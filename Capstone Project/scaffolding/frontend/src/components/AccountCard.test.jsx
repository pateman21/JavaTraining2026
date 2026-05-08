import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect } from "vitest";
import AccountCard from "./AccountCard.jsx";

const mockAccount = {
  accountId: "acc_1",
  accountType: "CHECKING",
  currency: "USD",
  balance: "1500.00",
};

describe("AccountCard", () => {
  it("renders account type", () => {
    render(
      <MemoryRouter>
        <AccountCard account={mockAccount} />
      </MemoryRouter>
    );
    expect(screen.getByText("CHECKING")).toBeInTheDocument();
  });

  it("renders currency and balance", () => {
    render(
      <MemoryRouter>
        <AccountCard account={mockAccount} />
      </MemoryRouter>
    );
    expect(screen.getByText(/USD 1500.00/)).toBeInTheDocument();
  });

  it("renders a link to the account detail page", () => {
    render(
      <MemoryRouter>
        <AccountCard account={mockAccount} />
      </MemoryRouter>
    );
    expect(screen.getByRole("link")).toHaveAttribute("href", "/accounts/acc_1");
  });
});
